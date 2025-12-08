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
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useAuth } from '../contexts/AuthContext';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);

        try {
            await login(email, password);
            notifications.show({
                title: '¡Bienvenido!',
                message: 'Inicio de sesión exitoso',
                color: 'green',
            });
            navigate('/');
        } catch (error: any) {
            notifications.show({
                title: 'Error',
                message: error.response?.data?.error || 'Error al iniciar sesión',
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
                            label="Email"
                            placeholder="tu@email.com"
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

                        <Button type="submit" fullWidth loading={loading}>
                            Iniciar sesión
                        </Button>

                        <Text c="dimmed" size="sm" ta="center">
                            ¿No tienes cuenta?{' '}
                            <Link to="/register" style={{ color: 'var(--mantine-color-blue-filled)' }}>
                                Regístrate
                            </Link>
                        </Text>
                    </Stack>
                </form>
            </Paper>
        </Container>
    );
}
