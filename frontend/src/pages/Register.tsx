import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
    Container,
    Paper,
    Title,
    TextInput,
    PasswordInput,
    Button,
    Text,
    Stack,
    ColorInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useAuth } from '../contexts/AuthContext';

export default function Register() {
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [avatarColor, setAvatarColor] = useState('#3b82f6');
    const [loading, setLoading] = useState(false);
    const { register } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);

        try {
            await register(username, email, password, avatarColor);
            notifications.show({
                title: '¡Cuenta creada!',
                message: 'Registro exitoso',
                color: 'green',
            });
            navigate('/');
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al registrarse',
                color: 'red',
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <Container size={420} my={100}>
            <Title ta="center" mb="xl">
                🎮 SquadSync
            </Title>

            <Paper withBorder shadow="md" p={30} radius="md">
                <form onSubmit={handleSubmit}>
                    <Stack>
                        <TextInput
                            label="Nombre de usuario"
                            placeholder="jugador123"
                            required
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                        />

                        <TextInput
                            label="Email"
                            placeholder="tu@email.com"
                            type="email"
                            required
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                        />

                        <PasswordInput
                            label="Contraseña"
                            placeholder="Tu contraseña"
                            required
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />

                        <ColorInput
                            label="Color de avatar"
                            placeholder="Elige tu color"
                            value={avatarColor}
                            onChange={setAvatarColor}
                        />

                        <Button type="submit" fullWidth loading={loading}>
                            Registrarse
                        </Button>

                        <Text c="dimmed" size="sm" ta="center">
                            ¿Ya tienes cuenta?{' '}
                            <Link to="/login" style={{ color: 'var(--mantine-color-blue-filled)' }}>
                                Inicia sesión
                            </Link>
                        </Text>
                    </Stack>
                </form>
            </Paper>
        </Container>
    );
}
